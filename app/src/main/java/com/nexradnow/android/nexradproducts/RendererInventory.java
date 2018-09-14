package com.nexradnow.android.nexradproducts;


import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Maintain a list of available renderers
 * Created by hobsonm on 10/15/15.
 */
public class RendererInventory {

    protected static Class knownClasses[] =
            {P19r0Renderer.class, P20MinusRRenderer.class, P37crRenderer.class, P38crRenderer.class};
    protected NexradRenderer[] renderers;
    protected String[] codes;
    protected String[] descriptions;

    public RendererInventory() {
        List<NexradRenderer> rendererList = new ArrayList<NexradRenderer>();
        for (Class<?> klass : knownClasses) {
            try {
                if (!Modifier.isAbstract(klass.getModifiers())) {
                    rendererList.add(((Class<NexradRenderer>) klass).newInstance());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("cannot instantiate a renderer", ex);
            }
        }
        Comparator<NexradRenderer> comp = new Comparator<NexradRenderer>() {
            @Override
            public int compare(NexradRenderer lhs, NexradRenderer rhs) {
                return lhs.getProductDescription().compareTo(rhs.getProductDescription());
            }
        };
        Collections.sort(rendererList, comp);
        renderers = rendererList.toArray(new NexradRenderer[0]);
        codes = new String[renderers.length];
        descriptions = new String[renderers.length];
        for (int index = 0; index < renderers.length; index++) {
            codes[index] = renderers[index].getProductCode();
            descriptions[index] = renderers[index].getProductDescription();
        }
    }

    public NexradRenderer[] getRenderers() {
        return renderers;
    }

    public String[] getCodes() {
        return codes;
    }

    public String[] getDescriptions() {
        return descriptions;
    }

    public NexradRenderer getRenderer(String code) {
        NexradRenderer renderer = null;
        for (int index = 0; index < renderers.length; index++ ) {
            if (code.equalsIgnoreCase(codes[index])) {
                renderer = renderers[index];
            }
        }
        return renderer;
    }
}
