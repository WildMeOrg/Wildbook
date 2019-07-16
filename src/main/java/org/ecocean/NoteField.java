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
    public static String buildHtmlDiv(String id, HttpServletRequest request, Shepherd myShepherd) {
        NoteField nf = build(id, myShepherd);
        if (nf == null) return "<!-- NoteField.build(" + id + ") failed -->";
        return nf.toHtmlDiv(request, myShepherd);
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

/*
TODO - more complex security (.owner ?)

    public static boolean canCreate(User u) {
        return !(u == null);
    }
    public boolean canEdit(User u) {
        return !(u == null);
    }
    public boolean canRead(User u) {
        return true;
    }
*/
    public boolean canEdit(HttpServletRequest request, Shepherd myShepherd) {
        if (request == null) return false;
        return request.isUserInRole("admin");
        //return canEdit(AccessControl.getUser(request, myShepherd));
    }
    public static boolean canCreate(HttpServletRequest request, Shepherd myShepherd) {
        if (request == null) return false;
        return request.isUserInRole("admin");
        //return canCreate(AccessControl.getUser(request, myShepherd));
    }
    public static boolean canRead(HttpServletRequest request, Shepherd myShepherd) {
        return true;
        //return canRead(AccessControl.getUser(request, myShepherd));
    }

    public String toHtmlDiv(HttpServletRequest request, Shepherd myShepherd) {
        String cont = this.getContent();
        if (!canRead(request, myShepherd)) return "<!-- 401 " + this.id + " -->";
        if (!canEdit(request, myShepherd)) {
            if (cont != null) return "<div class=\"org-ecocean-notefield-readonly\" id=\"id-" + this.id + "\">" + cont + "</div>";
            return "<div class=\"org-ecocean-notefield-need-default\" id=\"id-" + this.id + "\"></div>";  //have no content. default?
        }
        String h = "<div class=\"org-ecocean-notefield\" id=\"id-" + this.id + "\">";
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
