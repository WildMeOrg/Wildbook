package org.ecocean;

import org.ecocean.Util;
import org.ecocean.movement.Path;
import org.joda.time.DateTime;
import java.util.Set;
import java.util.HashSet;
import org.apache.commons.lang3.builder.ToStringBuilder;
/*
import java.util.Iterator;
import java.io.IOException;
import javax.jdo.Query;
import java.util.Collection;
*/

public class Route implements java.io.Serializable {

    private String id;
    private String name;
    private String locationId;
    private DateTime startTime;
    private DateTime endTime;
    private Path path;
    //private Set<User> users;

    public Route() {
        this.id = Util.generateUUID();
    }

    public String getId() {
        return id;
    }

    public DateTime getStartTime() {
        return startTime;
    }
    public void setStartTime(DateTime dt) {
        startTime = dt;
    }
    public DateTime getEndTime() {
        return endTime;
    }
    public void setEndTime(DateTime dt) {
        endTime = dt;
    }

    public String getLocationId() {
        return locationId;
    }
    public void setLocationId(String l) {
        locationId = l;
    }

/*  for now, Routes will be assigned in real-time so dont need this

    public Set<User> getUsers() {
        return users;
    }
    public void setUsers(Set<User> set) {
        users = set;
    }
    public void addUser(User user) {
        if (users == null) users = new HashSet<User>();
        users.add(user);
    }
*/

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
    }

    public Path getPath() {
        return path;
    }
    public void setPath(Path p) {
        path = p;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("locationId", locationId)
                .toString();
    }

}
