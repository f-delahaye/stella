package org.stella.droid.rest;

import org.stella.rest.StellaCommandService;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface StellaRestClient  {
    @GET(StellaCommandService.SAY_COMMAND_URI)
    public Call<String> say(@Header(StellaCommandService.AUTHORIZATION_HEADER) String user, @Path(StellaCommandService.COMMAND_PATH_VARIABLE) String command);
}
