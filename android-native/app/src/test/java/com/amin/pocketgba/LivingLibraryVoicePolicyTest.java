package com.amin.pocketgba;
import org.junit.Test;
import static org.junit.Assert.*;
public class LivingLibraryVoicePolicyTest {
    @Test public void onlyForegroundExactHomeMayUseVoice() {
        String home=LivingLibraryPolicy.REMOTE_HOME, origin="https://ken12121122-dotcom.github.io";
        assertTrue(LivingLibraryVoicePolicy.allowed(home,home,origin,true,true));
        assertFalse(LivingLibraryVoicePolicy.allowed(home,home,origin,false,true));
        assertFalse(LivingLibraryVoicePolicy.allowed(home,home,origin,true,false));
        assertFalse(LivingLibraryVoicePolicy.allowed(home,home,"https://evil.example",true,true));
        assertFalse(LivingLibraryVoicePolicy.allowed(home,origin+"/packagecanvas/",origin,true,true));
        assertFalse(LivingLibraryVoicePolicy.allowed(home+"?v=1",home+"?v=2",origin,true,true));
        assertFalse(LivingLibraryVoicePolicy.allowed(home+"#x",home+"#x",origin,true,true));
    }
    @Test public void sessionIsBoundedAndNotExecutableText() {
        assertTrue(LivingLibraryVoicePolicy.validSession("voice-123.abc"));
        assertFalse(LivingLibraryVoicePolicy.validSession(""));
        assertFalse(LivingLibraryVoicePolicy.validSession("x".repeat(81)));
        assertFalse(LivingLibraryVoicePolicy.validSession("');alert(1)//"));
    }
}
