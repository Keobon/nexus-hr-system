package com.nexuslabs.hr.domain.org.controller;

import com.nexuslabs.hr.domain.org.service.EmploymentTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 5장 — 고용형태(F-ORG-04). */
@RestController
@RequestMapping("/api/employment-types")
public class EmploymentTypeController extends OrgSettingItemController {

    public EmploymentTypeController(EmploymentTypeService service) {
        super(service);
    }
}
