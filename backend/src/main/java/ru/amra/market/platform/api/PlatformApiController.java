package ru.amra.market.platform.api;

import java.time.Clock;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.platform.generated.api.PlatformApi;
import ru.amra.market.platform.generated.model.ApiRootDto;

/** HTTP adapter exposing stable metadata for API discovery. */
@RestController
public final class PlatformApiController implements PlatformApi {

    private final Clock clock;

    public PlatformApiController(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ResponseEntity<ApiRootDto> getApiRoot() {
        var response =
                new ApiRootDto("amra-merch-market-backend", "v1", ApiRootDto.StatusEnum.AVAILABLE, Instant.now(clock));
        return ResponseEntity.ok(response);
    }
}
