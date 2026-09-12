package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

public class GearInFleetCoordinationDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "A commander coordinates movement across the entire fleet.";
    }

    public String[] getHighlights() {
        return new String[] {"entire fleet"};
    }

    public Color[] getHighlightColors() {
        return new Color[] {Misc.getHighlightColor()};
    }
}
