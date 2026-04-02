package com.fishclient.modules.radio;

public class RadioStation {

    private final String name;
    private final String genre;
    private final String streamUrl;

    public RadioStation(String name, String genre, String streamUrl) {
        this.name = name;
        this.genre = genre;
        this.streamUrl = streamUrl;
    }

    public String getName() {
        return name;
    }

    public String getGenre() {
        return genre;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    @Override
    public String toString() {
        return name + " (" + genre + ")";
    }
}

