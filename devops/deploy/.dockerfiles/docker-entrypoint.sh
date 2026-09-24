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
# exists the JVM silently SKIPS the dump, losing the evidence for the next OutOfMemoryError.
# Any HeapDumpPath already in JAVA_OPTS is replaced for the same reason.
HEAPDUMP_BASE=/usr/local/tomcat/logs/heapdumps
HEAPDUMP_DIR="$HEAPDUMP_BASE/$(date -u +%Y%m%dT%H%M%SZ)-$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')"
mkdir -p "$HEAPDUMP_DIR"
# Each dump is roughly the size of the heap (-Xmx). Keep only the newest earlier dump plus
# whatever this run writes, and drop empty directories from runs that never ran out of memory.
# Only directories under $HEAPDUMP_BASE are touched; older dumps elsewhere are left alone.
kept=0
for d in $(ls -1 "$HEAPDUMP_BASE" | sort -r); do
    p="$HEAPDUMP_BASE/$d"
    [ "$p" = "$HEAPDUMP_DIR" ] && continue
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
JAVA_OPTS="$(printf '%s' "$JAVA_OPTS" | sed 's/-XX:HeapDumpPath=[^ ]*//g') -XX:HeapDumpPath=$HEAPDUMP_DIR"
export JAVA_OPTS
echo "Heap dumps (on OutOfMemoryError) will be written to $HEAPDUMP_DIR"

# now run tomcat normally; exec so the JVM receives container signals directly (graceful
# SIGTERM shutdown, and `docker kill --signal=QUIT` prints a thread dump)
exec "$CATALINA_HOME/bin/catalina.sh" run
