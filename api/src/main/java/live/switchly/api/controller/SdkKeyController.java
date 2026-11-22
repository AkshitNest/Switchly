package live.switchly.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import live.switchly.api.model.SdkKey;
import live.switchly.api.security.UserPrincipal;
import live.switchly.api.service.SdkKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{project}/sdk-keys")
public class SdkKeyController {
    private final SdkKeyService service;
    public SdkKeyController(SdkKeyService service) { this.service = service; }

    public record CreateKey(@NotBlank @Size(max=255) String name,
                            @NotEmpty @Size(max=100) List<@NotNull @Pattern(regexp="[a-z0-9-]{1,255}") String> environmentKeys) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SdkKeyService.IssuedKey create(@PathVariable UUID project, @Valid @RequestBody CreateKey request,
                                          @AuthenticationPrincipal UserPrincipal user) {
        return service.create(project, request.name(), request.environmentKeys(), user.id());
    }

    @GetMapping
    public List<SdkKey> list(@PathVariable UUID project, @AuthenticationPrincipal UserPrincipal user) {
        return service.list(project, user.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID project, @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) {
        service.revoke(project, id, user.id());
    }
}
