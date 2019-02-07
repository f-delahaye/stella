package org.stella.droid.rest;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.stella.rest.StellaCommandService;

import java.io.IOException;

@RunWith(BlockJUnit4ClassRunner.class)
public class RetrofitClientFactoryTest {
    private RetrofitClientFactory factory;
    private MockWebServer server;

    @Before
    public void before() throws IOException {
        server = new MockWebServer();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseUrl = server.url("/");

        factory = new RetrofitClientFactory(baseUrl.url().toExternalForm());
    }

    @After
    public void after() throws IOException {
        server.shutdown();
    }

    @Test
    public void authorizationHeaderAdded() throws IOException, InterruptedException {

        // Start the server.
//        server.start();
        server.enqueue(new MockResponse().setBody("Frederic just said hello"));

        StellaRestClient client = factory.createClient(StellaRestClient.class);
        String response = client.say("Frederic", "hello").execute().body();
        RecordedRequest request = server.takeRequest();

        Assert.assertEquals("Frederic just said hello", response);
        Assert.assertEquals("Frederic", request.getHeader(StellaCommandService.AUTHORIZATION_HEADER));
        Assert.assertEquals("/say/hello", request.getPath());
    }
}
