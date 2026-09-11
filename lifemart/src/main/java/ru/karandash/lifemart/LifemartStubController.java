package ru.karandash.lifemart;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.karandash.contracts.menu.MenuSearchResponse;
import ru.karandash.contracts.menu.MenuSource;

@RestController
public class LifemartStubController {

    @GetMapping("/search")
    MenuSearchResponse search(@RequestParam String zone) {
        return MenuSearchResponse.emptyStub(MenuSource.LIFEMART, zone);
    }
}

