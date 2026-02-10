package com.lomekwi.cine.content.framed;

import com.lomekwi.cine.content.Clip;
import com.lomekwi.cine.resource.media.VdoRes;

public class VdoClip extends Clip<VdoRes> implements Framable{
    public VdoClip(VdoRes source, long inPoint) {
        super(source, inPoint);
    }
}
