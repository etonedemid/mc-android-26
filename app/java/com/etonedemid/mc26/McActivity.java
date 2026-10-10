package com.etonedemid.mc26;

import org.libsdl.app.SDLActivity;

// SDL3's activity, loading SDL and then the game library (libmc.so: the native image and glue.c, whose SDL_main starts
// the game).
public class McActivity extends SDLActivity {
    @Override protected String[] getLibraries() { return new String[] { "SDL3", "mc" }; }
}
