package com.nexuslabs.hr.domain.org.controller;

import com.nexuslabs.hr.domain.org.service.JobGradeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 5장 — 직급(F-ORG-03). */
@RestController
@RequestMapping("/api/job-grades")
public class JobGradeController extends OrgSettingItemController {

    public JobGradeController(JobGradeService service) {
        super(service);
    }
}
