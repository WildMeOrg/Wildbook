#!/usr/bin/perl
####
####  this is a one-time converter of existing .properties files into the sql to set "CONFIGURATION" table values
####    it will attempt to sanity-check for existence of definition files and fields for each value it sets
####

# usage:   requires 3 directory paths:
#   ./prop2sql.pl  /path/to/config-json  /path/wildbook/WEB-INF/classes/bundles  /path/wildbook_data_dir/WEB-INF/classes/bundles

use utf8;
use JSON;
use Data::Dumper;
use DBI;
my $KEY_DELIM = '_';

my ($DIR_DEFN, $DIR_DEFAULT, $DIR_OVERRIDE) = @ARGV;
die "usage:  $0   \\\n  /path/to/config-json  \\\n  /path/wildbook/WEB-INF/classes/bundles  \\\n  /path/wildbook_data_dir/WEB-INF/classes/bundles\n" unless $DIR_OVERRIDE;

my $defn = &read_definition($DIR_DEFN);

my $val;
&parse_props($DIR_DEFAULT);
&parse_props($DIR_OVERRIDE);
#print Dumper($val);
&generate_sql;



sub generate_sql {
    my $t = time;
    my $ct = 0;
    print "-- $0 generated " . localtime . " [$t]\n";
    foreach my $top (keys %$val) {
        print "\n\n-- $top\n";
        if (!$defn->{$top}) {
            print "--   missing definition file $DIR_DEFN/$top.json\n";
        } else {
            &check_defn($top, $val->{$top}, $defn->{$top});
        }
        printf(qq#DELETE FROM "CONFIGURATION" WHERE "ID" = %s;\nINSERT INTO "CONFIGURATION" VALUES (%s, %s, %d%03d, %d%03d);\n#,
            &dbquote($top), &dbquote($top), &dbquote(to_json($val->{$top}, { utf8 => 1 })), $t, $ct, $t, ($ct+1));
        $ct += 2;
    }
}


sub read_definition {
    my $dir = shift;
    my $defn;
    opendir(D, $dir) || die "no definition dir $dir";
    foreach my $d (readdir(D)) {
        next unless ($d =~/\.json$/);
        my $id = $`;
        open(J, "$dir/$d") || die "could not read $dir/$d";
        $defn->{$id} = from_json(join('', <J>));
        close(J);
    }
    return $defn;
}

sub parse_props {
    my $dir = shift;
    opendir(D, $dir) || die "no definition dir $dir";
    foreach my $p (readdir(D)) {
        next unless ($p =~/\.properties$/);
        my $id = $`;
        open(P, "$dir/$p") || die "could not read $dir/$p";
        while (<P>) {
            next if (/^(\s*#|\s*$)/);
            chop;
            my ($k, $v) = split(/\s*=\s*/, $_);
            $v = $` if ($v =~ /\s+$/);
            $k = uni($k);
            $v = uni($v);
#print "$id: $_]  ($k=$v)\n";
            my @key_path = split(/\./, $k);
            $val->{$id} = {} unless $val->{$id};
            &set($val->{$id}, \@key_path, $v, '');
        }
        close(P);
    }
}


sub check_defn {
    my ($prev, $v, $d) = @_;
#print "#####($prev)\n";
    return unless $v;
    if (ref $v eq 'HASH') {
        if (ref $d ne 'HASH') {
            print "--   no defn for $prev\n";
            return;
        }
        foreach my $k (keys %$v) {
            if ($k eq '__value') {
                ## skip this
            } elsif (!$d->{$k}) {
                #print "--   $prev.$k has no defn (1); value = " . to_json($v->{$k}) . "\n";
                warn "--   $prev.$k has no defn (1); value = " . Dumper($v->{$k}) . "\n";
            } else {
                &check_defn("$prev.$k", $v->{$k}, $d->{$k});
            }
        }
    } else {
        if (!$d) {
            print "--   $prev.$v has no defn (2)\n";
        }
    }
}

sub uni {
    my $in = shift;
    while ($in =~ /\\u([0-9a-f]{4})/i) {
        $in = $` . chr(hex($1)) . $';
    }
    return $in;
}

sub set {
    my ($j, $path, $val, $key_prefix) = @_;
#warn Dumper($j) . Dumper($path) . "val=($val)\n";
    next unless @$path;
    if (scalar(@$path) == 1) {
#warn "$key_prefix($path->[0])";
        my $cval = $val;
        if ($cval =~ /^true$/i) {
            $cval = JSON::true;
        } elsif ($cval =~ /^false$/i) {
            $cval = JSON::false;
        } elsif ($cval =~ /^[\d\.\-]+$/i) {
            $cval = $cval + 0;
        }
        $j->{$path->[0]} = { '__value' => $cval };
#print "(A:$path->[0]) " . Dumper($j);
        return;
    }
    my $k = shift @$path;
    if (!$j->{$k}) {
        my $key_id = &key_id($key_prefix, [$k]);
        $j->{$k} = {
            #id => $key_id,
        };
    }
    &set($j->{$k}, $path, $val, $key_prefix . $KEY_DELIM . $k);
#print "(B)" . Dumper($j);
}

sub key_id {
    my ($prefix, $key_path) = @_;
    return join($KEY_DELIM, $prefix, @$key_path);
}


sub dbquote {
    return DBD::_::db->quote(shift);
}
