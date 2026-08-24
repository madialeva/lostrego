module dev.ktai.lostrego.spy {
    requires dev.ktai.lostrego;
    requires dev.ktai.lostrego.spy.common;
    requires info.picocli;

    opens dev.ktai.lostrego.spy to info.picocli;
}
