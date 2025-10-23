package com.example.galggg.vpn;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.example.galggg.vpn.shadows.ShadowOs;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = ShadowOs.class)
public class XrayRunnerRoutingTest {

    @Test
    public void udpRulesAreConfiguredInOrder() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        XrayRunner runner = new XrayRunner(ctx, null);

        VlessLink v = new VlessLink(
                "00000000-0000-0000-0000-000000000000",
                "example.com",
                443,
                "example.com",
                "publicKeyValue",
                "shortId",
                null,
                null,
                null,
                null
        );

        Method write = XrayRunner.class.getDeclaredMethod("writeXrayConfig", VlessLink.class);
        write.setAccessible(true);

        File cfgFile = (File) write.invoke(runner, v);
        String json = new String(Files.readAllBytes(cfgFile.toPath()), StandardCharsets.UTF_8);
        Log.d("XrayRunnerRoutingTest", "config preview: " + json);
        System.out.println("xray client config at: " + cfgFile.getAbsolutePath());

        JSONObject root = new JSONObject(json);
        JSONArray rules = root.getJSONObject("routing").getJSONArray("rules");
        System.out.println("routing rules: " + rules.toString());
        assertEquals("dns-out", rules.getJSONObject(0).getString("outboundTag"));
        assertEquals("block", rules.getJSONObject(1).getString("outboundTag"));
    }
}
