package org.stella.restbridge;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.stella.rest.StellaUserService;

@RestController
public class StellaRestController {

    @RequestMapping(StellaUserService.WELCOME_ANONYMOUS_COMMAND)
    public @ResponseBody String welcome() {
        return "Are you a new member?";
    }

    @RequestMapping(StellaUserService.WELCOME_COMMAND)
    public @ResponseBody String welcome(@RequestHeader(StellaUserService.AUTHORIZATION_HEADER) String user) {
        return "Welcome "+user;
    }
}
