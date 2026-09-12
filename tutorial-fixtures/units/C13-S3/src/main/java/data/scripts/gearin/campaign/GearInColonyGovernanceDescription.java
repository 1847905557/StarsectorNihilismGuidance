package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

public class GearInColonyGovernanceDescription implements DescriptionSkillEffect {
    public Color getTextColor() {
        return null;
    }

    public String getString() {
        return "An administrator turns one governed market into a steadier source of income.";
    }

    public String[] getHighlights() {
        return new String[] {"one governed market", "income"};
    }

    public Color[] getHighlightColors() {
        Color color = Misc.getHighlightColor();
        return new Color[] {color, color};
    }
}
