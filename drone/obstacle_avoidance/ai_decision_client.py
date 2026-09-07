import json
import os

import httpx


VALID_ACTIONS = {
    "UP",
    "LEFT",
    "RIGHT",
    "BACK",
    "DOWN",
    "YAW_LEFT",
    "YAW_RIGHT",
    "HOVER",
}


SYSTEM_PROMPT = """
You are the autonomous obstacle avoidance decision controller for a PX4 drone.

The drone has ALREADY stopped and is HOVERING before you are called.

You receive a FRESH LiDAR scan and current flight state after the drone has stabilized.

You must choose exactly ONE next avoidance action from:

UP
LEFT
RIGHT
BACK
DOWN
YAW_LEFT
YAW_RIGHT
HOVER


MISSION

Safely avoid the obstacle so the drone can eventually continue its previous
flight direction.

You are responsible for deciding the avoidance direction.

The local controller is responsible only for:
- emergency hover
- validating your requested action
- executing the action with bounded speed and duration
- stopping again after the action
- collecting a new LiDAR scan
- calling you again with the updated state


AVAILABLE SENSOR INFORMATION

The state may contain:

status
nearest_direction
nearest_distance_m
nearest_angle_deg

front_m
front_left_m
front_right_m

left_m
right_m

back_left_m
back_m
back_right_m

altitude_m
min_altitude_m
max_altitude_m

yaw_deg

goal_direction
previous_action
recent_actions
rejected_actions
last_rejection_reason

prefer_up
vertical_allowed
up_temporarily_ineffective

obstacle_threshold_m
avoidance_attempt


DECISION STRATEGY

1. Analyze the FRESH sensor state before choosing any movement.

2. When the original goal is FORWARD, analyze:
   FRONT
   FRONT_LEFT
   FRONT_RIGHT

3. When the forward path is blocked, prefer UP first when:
   - prefer_up is true
   - vertical_allowed is true
   - altitude_m is below max_altitude_m
   - up_temporarily_ineffective is false

The primary avoidance strategy is to climb over the obstacle. Only consider
LEFT, RIGHT, BACK, DOWN, or YAW after UP is unavailable, unsafe, altitude
limited, or proven ineffective by repeated fresh scans.

If a tall obstacle remains in front while altitude is still below
max_altitude_m, continuing UP is valid even when forward clearance has not
improved yet. Once the forward path is clear above the obstacle, the local
controller will resume forward flight.

4. Do NOT choose UP when:
   - vertical_allowed is false
   - altitude is at or above max altitude
   - up_temporarily_ineffective is true
   - previous UP attempts failed to improve clearance

5. If UP is not appropriate, compare LEFT and RIGHT.

6. LEFT should only be considered when LEFT and FRONT_LEFT have sufficient
   clearance.

7. RIGHT should only be considered when RIGHT and FRONT_RIGHT have sufficient
   clearance.

8. Prefer the horizontal direction with greater verified clearance.

9. BACK should only be considered when:
   BACK
   BACK_LEFT
   BACK_RIGHT
   all indicate sufficient clearance.

10. DOWN is a lower-priority escape action.
    Never choose DOWN when altitude is at or below min_altitude_m.

11. YAW_LEFT or YAW_RIGHT may be used when translation paths are poor and
    rotating may help search for a better heading.

12. HOVER is LAST RESORT ONLY.

Do NOT choose HOVER when at least one movement action can be reasonably
justified.

If FRONT / FRONT_LEFT / FRONT_RIGHT are blocked and:
- prefer_up is true
- vertical_allowed is true
- altitude_m < max_altitude_m
- up_temporarily_ineffective is false

then strongly prefer UP.

If UP is unavailable, choose the safest verified LEFT, RIGHT, or BACK action.

Only output HOVER when:
- every translational escape direction is unsafe or unavailable
- vertical movement is unavailable
- yaw would not reasonably improve the situation
- or sensor state is insufficient to justify any movement

When status is EMERGENCY and the obstacle is in FRONT, do not remain HOVERING
indefinitely.
Choose the safest available escape action.


ACTION HISTORY RULES

Use previous_action, recent_actions, rejected_actions and
last_rejection_reason.

Do not repeatedly choose an action that is failing.

If an action was rejected by the local safety controller, do not immediately
request the same action again unless the sensor state has clearly changed.

Avoid oscillation such as:

LEFT -> RIGHT -> LEFT -> RIGHT

RIGHT -> LEFT -> RIGHT -> LEFT

YAW_LEFT -> YAW_RIGHT -> YAW_LEFT

YAW_RIGHT -> YAW_LEFT -> YAW_RIGHT


IMPORTANT SAFETY RULES

Never intentionally move toward a blocked direction.

Never intentionally move toward the nearest obstacle.

Never exceed max_altitude_m.

Never descend below min_altitude_m.

Do not invent sensor information.

Do not choose speed.

Do not choose movement duration.

Do not choose coordinates.

Do not output MAVSDK commands.

You only choose ONE high-level action.

After your action is executed, the drone will HOVER again, obtain a FRESH
LiDAR scan, and ask you for another decision.

Therefore make ONE bounded decision based only on the CURRENT state.


OUTPUT FORMAT

IMPORTANT OUTPUT RULE:

You MUST return exactly one valid action token.

Never return an empty response.

Never explain your reasoning.

Never output analysis.

Never output JSON.

Never output Markdown.

If multiple actions are possible, choose the safest one immediately.

If uncertain between safe actions, prefer in this order:

UP
LEFT
RIGHT
BACK
YAW_LEFT
YAW_RIGHT
DOWN
HOVER


Output exactly ONE of these tokens:

UP
LEFT
RIGHT
BACK
DOWN
YAW_LEFT
YAW_RIGHT
HOVER

Output the token only.

Do not explain.
Do not use Markdown.
Do not add punctuation.
Do not add any other text.
""".strip()


class AiDecisionClient:
    def __init__(self) -> None:
        self.api_key = os.getenv(
            "OPENROUTER_API_KEY",
            "",
        ).strip()

        self.base_url = os.getenv(
            "OPENROUTER_BASE_URL",
            "https://openrouter.ai/api/v1",
        ).rstrip("/")

        self.model = os.getenv(
            "OPENROUTER_MODEL",
            "openrouter/free",
        ).strip()

        self.timeout_seconds = float(
            os.getenv(
                "AI_DECISION_TIMEOUT_S",
                "8.0",
            )
        )

        self.max_tokens = int(
            os.getenv(
                "AI_DECISION_MAX_TOKENS",
                "128",
            )
        )

        print(
            "[AI] ========================================\n"
            "[AI] Provider : OpenRouter\n"
            f"[AI] Base URL : {self.base_url}\n"
            f"[AI] Model    : {self.model}\n"
            "[AI] Mode     : AI DECIDES AVOIDANCE\n"
            "[AI] ========================================",
            flush=True,
        )

    async def choose_action(self, state: dict) -> str:
        """
        OpenRouter AI decides exactly one avoidance action.

        Local code does not select another movement direction if AI fails.
        Failure or invalid model output always results in HOVER.
        """

        if not self.api_key:
            print(
                "[AI] OPENROUTER_API_KEY missing -> HOVER",
                flush=True,
            )
            return "HOVER"

        print(
            "[AI] Sending fresh sensor state to OpenRouter...",
            flush=True,
        )

        try:
            async with httpx.AsyncClient(
                    timeout=self.timeout_seconds,
            ) as client:
                response = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": self.model,
                        "messages": [
                            {
                                "role": "system",
                                "content": SYSTEM_PROMPT,
                            },
                            {
                                "role": "user",
                                "content": json.dumps(
                                    state,
                                    ensure_ascii=False,
                                    separators=(",", ":"),
                                ),
                            },
                        ],
                        "temperature": 0,
                        "max_tokens": self.max_tokens,
                    },
                )

                response.raise_for_status()
                payload = response.json()

        except Exception as exc:
            print(
                f"[AI] OpenRouter request failed: {exc}",
                flush=True,
            )
            print(
                "[AI] Safety action -> HOVER",
                flush=True,
            )
            return "HOVER"

        try:
            choice = payload["choices"][0]
            message = choice.get("message") or {}

            content = message.get("content") or ""

            action = content.strip().upper()

            actual_model = payload.get(
                "model",
                self.model,
            )

            provider = payload.get(
                "provider",
                "unknown",
            )

            finish_reason = choice.get(
                "finish_reason",
                "unknown",
            )

            print(
                "[AI-RESPONSE]\n"
                f"MODEL      : {actual_model}\n"
                f"PROVIDER   : {provider}\n"
                f"FINISH     : {finish_reason}\n"
                f"DECISION   : {action or '<EMPTY>'}",
                flush=True,
            )

        except Exception as exc:
            print(
                f"[AI] Invalid OpenRouter response: {exc}",
                flush=True,
            )
            return "HOVER"

        if action not in VALID_ACTIONS:
            print(
                f"[AI] Invalid action: {action or '<EMPTY>'} -> HOVER",
                flush=True,
            )
            return "HOVER"

        print(
            f"[AI] FINAL DECISION: {action}",
            flush=True,
        )

        return action
