package com.lomekwi.cave.task;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Json.Serializable;
import com.badlogic.gdx.utils.JsonValue;
import com.lomekwi.cave.util.Units;

/**
 * 导出参数数据模型，使用 libGDX {@link Json} 序列化。
 */
public class ExportOptions implements Serializable {
    public String outputPath;
    public int width;
    public int height;
    public double fps;
    public int bitrate;

    public ExportOptions() {}

    public ExportOptions(String outputPath, int width, int height, double fps, int bitrate) {
        this.outputPath = outputPath;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrate = bitrate;
    }

    public void setFrom(ExportOptions other) {
        outputPath = other.outputPath;
        width = other.width;
        height = other.height;
        fps = other.fps;
        bitrate = other.bitrate;
    }

    /** bitrate Mbps 的便捷 getter/setter（存为 bps） */
    public double getBitrateMbps() {
        return bitrate / (double) Units.MEGA;
    }

    public void setBitrateMbps(double mbps) {
        this.bitrate = (int) (mbps * Units.MEGA);
    }

    @Override
    public void write(Json json) {
        json.writeValue("outputPath", outputPath);
        json.writeValue("width", width);
        json.writeValue("height", height);
        json.writeValue("fps", fps);
        json.writeValue("bitrateMbps", getBitrateMbps());
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        outputPath = jsonData.getString("outputPath", "");
        width = jsonData.getInt("width", 1920);
        height = jsonData.getInt("height", 1080);
        fps = jsonData.getDouble("fps", 30.0);
        setBitrateMbps(jsonData.getDouble("bitrateMbps", 6.0));
    }
}
