#!/bin/sh

# bad code begets worse code
mkdir /data
ln -s /usr/local/tomcat/webapps/wildbook_data_dir /data/

#apt-get update -qq
#apt-get install -y -qq imagemagick
apt-get update
apt-get install -y imagemagick
echo Done pre-initializing Wildbook.

# Heap dumps go to a fresh directory per JVM start. With a fixed directory the JVM names the
# file java_pid<N>.hprof, and container PIDs repeat across restarts: when that file already
# exists the JVM SKIPS the dump ("Unable to create ...: File exists"), losing the evidence for
# the next OutOfMemoryError. Any HeapDumpPath already in JAVA_OPTS is replaced for the same
# reason (paths containing spaces are not supported). A dump is best effort: it needs free disk
# roughly the size of the heap (-Xmx), and only JVM-detected heap/metaspace exhaustion triggers
# it (not, e.g., direct-buffer limits or failure to create a native thread).
HEAPDUMP_BASE=/usr/local/tomcat/logs/heapdumps
if mkdir -p "$HEAPDUMP_BASE"; then
    # Prune first so a nearly full disk gets its space back before this run's directory is made.
    # Keep only the newest earlier dump and drop empty directories from runs that never ran out
    # of memory. Only directories named like ours (YYYYMMDDTHHMMSSZ-xxxxxxxx) are touched.
    kept=0
    for d in $(ls -1 "$HEAPDUMP_BASE" | sort -r); do
        case "$d" in
            [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]T[0-9][0-9][0-9][0-9][0-9][0-9]Z-*) ;;
            *) continue ;;
        esac
        p="$HEAPDUMP_BASE/$d"
        [ -d "$p" ] || continue
        if [ -z "$(ls -A "$p")" ]; then
            rmdir "$p"
        elif [ "$kept" -eq 0 ]; then
            kept=1
        else
            echo "Removing old heap dump $p"
            rm -rf "$p"
        fi
    done
    HEAPDUMP_DIR="$HEAPDUMP_BASE/$(date -u +%Y%m%dT%H%M%SZ)-$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')"
    # plain mkdir (no -p) fails if the name already exists, so the directory is ours alone
    if mkdir "$HEAPDUMP_DIR"; then
        JAVA_OPTS="$(printf '%s' "$JAVA_OPTS" | sed 's/-XX:HeapDumpPath=[^ ]*//g') -XX:HeapDumpPath=$HEAPDUMP_DIR"
        export JAVA_OPTS
        echo "Heap dumps (on OutOfMemoryError) will be written to $HEAPDUMP_DIR"
    else
        echo "WARNING: could not create $HEAPDUMP_DIR; leaving HeapDumpPath unchanged"
    fi
else
    echo "WARNING: could not create $HEAPDUMP_BASE; leaving HeapDumpPath unchanged"
fi

# now run tomcat normally; exec so the JVM receives container signals directly (graceful
# SIGTERM shutdown, and `docker kill --signal=QUIT` prints a thread dump)
exec "$CATALINA_HOME/bin/catalina.sh" run
