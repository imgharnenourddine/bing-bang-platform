package ma.enset.bigbang.participant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(@NotBlank @Size(max = 100) String firstName,
                                  @NotBlank @Size(max = 100) String lastName) {
    public RegistrationRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
    }
}
