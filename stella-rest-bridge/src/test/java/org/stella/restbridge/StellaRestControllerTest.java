package org.stella.restbridge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.stella.rest.StellaUserService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class StellaRestControllerTest{

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnWelcomeMessage() throws Exception {
        this.mockMvc.perform(get(StellaUserService.WELCOME_COMMAND).header(StellaUserService.AUTHORIZATION_HEADER, "frederic")).
                andDo(print()).andExpect(status().isOk()).
                andExpect(content().string(containsString("Welcome frederic")));
    }
}
