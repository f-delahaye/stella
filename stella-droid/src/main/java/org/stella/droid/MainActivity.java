package org.stella.droid;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import org.stella.droid.rest.RetrofitClientFactory;
import org.stella.droid.rest.StellaRestClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String STELLA_TAG = "Stella";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        final EditText answer = findViewById(R.id.txt);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new RetrofitClientFactory().createClient(StellaRestClient.class).say("Frederic", answer.getText().toString()).enqueue(
                        new Callback<String>() {
                            @Override
                            public void onResponse(Call<String> call, Response<String> response) {
                                answer.setText(response.body());
                            }

                            @Override
                            public void onFailure(Call<String> call, Throwable t) {
                                answer.setText("rest call failed "+t);
                            }
                        }
                );
            }
        });
    }

}
