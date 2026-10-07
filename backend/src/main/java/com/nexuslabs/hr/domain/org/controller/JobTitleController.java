package com.nexuslabs.hr.domain.org.controller;

import com.nexuslabs.hr.domain.org.service.JobTitleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 5장 — 직책(F-ORG-03). */
@RestController
@RequestMapping("/api/job-titles")
public class JobTitleController extends OrgSettingItemController {

    public JobTitleController(JobTitleService service) {
        super(service);
    }
}
