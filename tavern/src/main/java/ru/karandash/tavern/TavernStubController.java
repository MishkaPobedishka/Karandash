package ru.karandash.tavern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.karandash.contracts.menu.AdapterStatusResponse;
import ru.karandash.contracts.menu.MenuSource;

@RestController
@RequestMapping({"/stub", "/sd-menu/stub"})
public class TavernStubController {

    @GetMapping("/status")
    AdapterStatusResponse status() {
        return AdapterStatusResponse.stub(MenuSource.TAVERN);
    }
}

