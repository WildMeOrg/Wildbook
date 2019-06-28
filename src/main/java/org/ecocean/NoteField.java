// kind of experimental swiss-army knife text-like field
//   (probably intended to contain markdown content)

/*

NOTES
- https://github.com/ssaarela/javersion
- https://javers.org/

TODO i18n etc.

*/

package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.json.JSONObject;

public class NoteField implements java.io.Serializable {

    private String id;
    private String content;  //we will grow this later!  :)


    public NoteField() {
        id = Util.generateUUID();
    }

    public NoteField(String content) {
        this();
        this.content = content;
    }

    //this loads the NoteField if it exists, otherwise it creates one
    // NOTE: this does not persist the new one.  that should/can be done by editing servlet
    public static NoteField build(String id, Shepherd myShepherd) {
        if (!Util.isUUID(id)) return null;
        NoteField nf = null;
        try {
            nf = (NoteField)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(NoteField.class, id), true));
        } catch (Exception ex) {}
        if (nf != null) return nf;
        nf = new NoteField();
        nf.id = id;
        return nf;
    }
    public static String buildHtmlDiv(String id, Shepherd myShepherd) {
        NoteField nf = build(id, myShepherd);
        if (nf == null) return "<!-- NoteField.build(" + id + ") failed -->";
        return nf.toHtmlDiv();
    }

    public String getContent() {
        return content;
    }

    //later we might let this make revisions etc (or a subclass?)
    public void setContent(String c) {
        content = c;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }

    //TODO FIXME real security.  :)
    public static boolean canCreate(User u) {
        return !(u == null);
    }
    public boolean canEdit(User u) {
        return !(u == null);
    }
    public boolean canEdit(HttpServletRequest request, Shepherd myShepherd) {
        return canEdit(AccessControl.getUser(request, myShepherd));
    }
    public static boolean canCreate(HttpServletRequest request, Shepherd myShepherd) {
        return canCreate(AccessControl.getUser(request, myShepherd));
    }

    public String toHtmlDiv() {
        String h = "<div class=\"org-ecocean-notefield\" id=\"" + this.id + "\">";
        String cont = this.getContent();
        if (cont != null) h += cont;
        h += "</div>";
        return h;
    }

    public JSONObject toJSONObject() {
        JSONObject j = new JSONObject();
        j.put("id", this.id);
        j.put("content", this.getContent());
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append((content == null) ? "content=(null)" : "content.length=" + content.length())
                .toString();
    }

}
