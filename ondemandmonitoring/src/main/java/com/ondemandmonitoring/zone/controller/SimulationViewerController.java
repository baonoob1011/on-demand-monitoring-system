package com.ondemandmonitoring.zone.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SimulationViewerController {

    @GetMapping("/simulation-viewer")
    public String simulationViewer() {
        return "redirect:/simulation-viewer/index.html";
    }
}
