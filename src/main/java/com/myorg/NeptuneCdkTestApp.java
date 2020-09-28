package com.myorg;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class NeptuneCdkTestApp {
    public static void main(final String[] args) {
        App app = new App();

        new NeptuneCdkTestStack(app, "NeptuneCdkTestStack");

        app.synth();
    }
}
