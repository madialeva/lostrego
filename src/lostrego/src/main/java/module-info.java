module dev.ktai.lostrego {
    exports dev.ktai.lostrego.core;

    uses dev.ktai.lostrego.core.spi.CaptureProvider;

    provides dev.ktai.lostrego.core.spi.CaptureProvider
        with dev.ktai.lostrego.backend.libpcap.LibpcapProvider;
}
