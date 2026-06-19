package com.lomekwi.cave.resource.media;

import java.io.File;

public record MediaCreatedEvent(File file, MedRes medRes) {
}
