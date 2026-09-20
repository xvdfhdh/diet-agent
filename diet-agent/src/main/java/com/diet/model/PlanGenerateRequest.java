package com.diet.model;

import com.diet.enums.AcquisitionMode;

import java.time.LocalDate;

public record PlanGenerateRequest(LocalDate weekStart, AcquisitionMode preferredMode) { }
