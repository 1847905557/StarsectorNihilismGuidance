package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

public class GearInCaptainMobilityDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "A captain keeps one ship responsive under fire.";
    }

    public String[] getHighlights() {
        return new String[] {"one ship", "responsive"};
    }

    public Color[] getHighlightColors() {
        Color color = Misc.getHighlightColor();
        return new Color[] {color, color};
    }
}
