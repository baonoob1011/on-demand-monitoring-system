package com.ondemandmonitoring.missionv2.service;

import java.util.List;

import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.missionv2.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.missionv2.dto.request.AssignStaffRequest;
import com.ondemandmonitoring.missionv2.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.missionv2.dto.request.MissionUpdateRequest;
import com.ondemandmonitoring.missionv2.dto.response.MissionV2Response;

public interface IMissionV2Service {

    MissionV2Response createMission(MissionCreateRequest request);

    MissionV2Response updateMission(String missionId, MissionUpdateRequest request);

    MissionV2Response assignDevice(String missionId, AssignDeviceRequest request);

    MissionV2Response assignStaff(String missionId, AssignStaffRequest request);

    List<MissionV2Response> getAllMissions();
}
