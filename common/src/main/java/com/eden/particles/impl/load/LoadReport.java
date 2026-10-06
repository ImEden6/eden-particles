package com.eden.particles.impl.load;

import java.util.List;

public record LoadReport(int effects, int templates, List<DefinitionError> errors) {

    public String summary() {
        String counts = "Loaded " + effects + (effects == 1 ? " effect" : " effects")
                + " and " + templates + (templates == 1 ? " template" : " templates");
        return errors.isEmpty() ? counts : counts + " (" + errors.size() + (errors.size() == 1 ? " error)" : " errors)");
    }
}
