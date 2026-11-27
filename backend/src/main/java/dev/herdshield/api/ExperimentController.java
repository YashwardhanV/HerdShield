package dev.herdshield.api;

import dev.herdshield.experiment.ExperimentRequest;
import dev.herdshield.experiment.ExperimentResult;
import dev.herdshield.experiment.ExperimentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentService experiments;

    public ExperimentController(ExperimentService experiments) {
        this.experiments = experiments;
    }

    @PostMapping
    public ExperimentResult run(@Valid @RequestBody ExperimentRequest request) {
        return experiments.run(request);
    }
}
