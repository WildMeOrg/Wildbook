#!/usr/bin/perl

# get the input data with something like below:
=pod

select
    "ANNOTATION"."ID" as annotId,
    "ANNOTATION"."ACMID" as annotAcmId,
    "ENCOUNTER"."CATALOGNUMBER" as encId,
    "ENCOUNTER"."INDIVIDUALID" as indivId
from
    "ANNOTATION" join "ENCOUNTER_ANNOTATIONS" on ("ENCOUNTER_ANNOTATIONS"."ID_EID" = "ANNOTATION"."ID")
    join "ENCOUNTER" on ("ENCOUNTER_ANNOTATIONS"."CATALOGNUMBER_OID" = "ENCOUNTER"."CATALOGNUMBER")
where
    "ANNOTATION"."ACMID" IS NOT NULL
order by
    annotAcmId, indivId
;

=cut

use Data::Dumper;

my %acm;
while (<>) {
    chop;
    $_ = $' if (/^\s+/);
    my @f = split(/\s+\|\s+/, $_);
    next unless (length($f[0]) == 36);
    #print join("%", @f) . "\n";
    push(@{$acm{$f[1]}}, [ $f[0], $f[2], $f[3] ]);
}

my $mis_all;
for my $acmId (sort keys %acm) {
    next if (scalar(@{$acm{$acmId}}) < 2);
    my $name = undef;
    my $mismatch = 0;
    for my $d (@{$acm{$acmId}}) {
        if (!$name && $d->[2]) {
            $name = $d->[2];
        } elsif ($name && $d->[2] && ($name ne $d->[2])) {
            $mismatch = 1;
        }
    }
    if ($mismatch) {
        $mis_all .= "-- MISMATCH on $acmId:\n";
        for my $d (@{$acm{$acmId}}) {
            $mis_all .= "--    ann=$d->[0] enc=$d->[1] mismatch name: $d->[2]\n";
            $mis_all .= sprintf(qq#--    UPDATE "ANNOTATION" SET "MATCHAGAINST"='f' WHERE "ID"='%s';\n#, $d->[0]);
        }
        $mis_all .= "\n";

    } elsif (!$name) {
        print "-- no name(s) on $acmId, leaving untouched\n\n";

    } else {
        print "-- $acmId determined to be $name:\n";
        my $ct = 0;
        for my $d (@{$acm{$acmId}}) {
            $ct++;
            if ($d->[2] eq $name) {
                print "--    [$ct] ann=$d->[0] enc=$d->[1] already named; skipping\n"
            } else {
                print "--    [$ct] ann=$d->[0] enc=$d->[1] UNNAMED:\n";
                printf(qq#          UPDATE "ANNOTATION" SET "MATCHAGAINST"='f' WHERE "ID"='%s';\n#, $d->[0]);
                printf(qq#          UPDATE "ENCOUNTER" SET "INDIVIDUALID"='%s' WHERE "CATALOGNUMBER"='%s';\n#, $name, $d->[1]);
            }
        }
        print "\n";
    }
}

print "\n\n\n$mis_all";

