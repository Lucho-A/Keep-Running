package com.lucho.keeprunning;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class TextToVoice {
    private TextToSpeech tts;

    public TextToVoice(Context mContext) {tts =new TextToSpeech(mContext, status ->tts.setLanguage(new Locale("es","LA")));}

    public void text_to_voice(String msj){tts.speak(msj, TextToSpeech.QUEUE_FLUSH, null);}
}
