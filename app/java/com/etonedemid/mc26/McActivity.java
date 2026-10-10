package com.etonedemid.mc26;

import android.content.pm.ActivityInfo;
import org.libsdl.app.SDLActivity;

// SDL3's activity, loading SDL and then the game library (libmc.so: the native image and glue.c, whose SDL_main starts
// the game).
public class McActivity extends SDLActivity {
    @Override protected String[] getLibraries() { return new String[] { "SDL3", "mc" }; }

    // Both landscapes by the sensor, whatever the rotation lock says (SDL would pick USER_LANDSCAPE, which follows the
    // lock and so stays in one), like the 1.12.2 build's sensorLandscape. vkshim.c keeps the picture upright either way.
    @Override public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }
}
