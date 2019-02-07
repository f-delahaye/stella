package org.stella.restbridge;

import org.springframework.web.bind.annotation.*;
import org.stella.rest.StellaCommandService;

@RestController
public class StellaRestController {

    @RequestMapping(StellaCommandService.SAY_COMMAND_URI)
    public @ResponseBody String say(@RequestHeader(StellaCommandService.AUTHORIZATION_HEADER) String user, @PathVariable(StellaCommandService.COMMAND_PATH_VARIABLE) String command) {
        System.out.println(user+" just said "+command);
        return user+" just said "+command;
    }
}
