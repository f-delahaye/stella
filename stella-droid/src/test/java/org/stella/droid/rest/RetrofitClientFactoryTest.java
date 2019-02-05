package org.stella.droid.rest;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.builders.JUnit4Builder;
import org.junit.internal.runners.JUnit4ClassRunner;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.stella.rest.StellaUserService;

import java.io.IOException;

@RunWith(BlockJUnit4ClassRunner.class)
public class RetrofitClientFactoryTest {
    private RetrofitClientFactory factory;
    private MockWebServer server;

    @Before
    public void before() throws IOException {
        server = new MockWebServer();

        // Schedule some responses.
        server.enqueue(new MockResponse().setBody("Welcome"));

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
        server.enqueue(new MockResponse().setBody("Welcome"));

        StellaRestClient client = factory.createClient(StellaRestClient.class);
        String response = client.welcome("Frederic").execute().body();
        RecordedRequest request = server.takeRequest();

        Assert.assertEquals("Welcome", response);
        Assert.assertEquals("Frederic", request.getHeader(StellaUserService.AUTHORIZATION_HEADER));
    }
}
