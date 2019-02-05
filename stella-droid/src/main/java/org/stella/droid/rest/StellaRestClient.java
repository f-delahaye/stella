package org.stella.droid.rest;

import org.stella.rest.StellaUserService;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface StellaRestClient  {
    @GET(StellaUserService.WELCOME_COMMAND)
    public Call<String> welcome(@Header(StellaUserService.AUTHORIZATION_HEADER) String user);
}
