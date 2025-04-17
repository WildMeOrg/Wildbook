#!/usr/bin/perl

#### "expert use only"   :)   :(    ask jon?

use DBI;
use JSON;
use Data::Dumper;

my $db = DBI->connect('dbi:Pg:dbname=mantamatcher;host=localhost', 'wildbook', 'wildbook', {AutoCommit => 0, RaiseError => 1}) or die $DBI::errstr;

my %ma_map;

my $q = $db->prepare('SELECT * FROM "MEDIAASSET" WHERE "PARENTID" IS NULL');
$q->execute;
while (my $r = $q->fetchrow_hashref) {
    #print Dumper($r);
    next unless $r->{PARAMETERS};
    my $p = from_json($r->{PARAMETERS});
    #print Dumper($p);
    next unless $p->{sourceSinglePhotoVideoID};
    if ($ma_map{$p->{sourceSinglePhotoVideoID}}) {
        print Dumper($r);
        print "DUPLICATED $p->{sourceSinglePhotoVideoID}!!!";
        die;
    }
    $ma_map{$p->{sourceSinglePhotoVideoID}} = $r->{ID};
}
#print Dumper(\%ma_map);

$q = $db->prepare('SELECT * FROM "SINGLEPHOTOVIDEO_KEYWORDS"');
$q->execute;
while (my $r = $q->fetchrow_hashref) {
#$VAR1 = {
          #'IDX' => 2,
          #'INDEXNAME_EID' => 'Scartrailingedge',
          #'DATACOLLECTIONEVENTID_OID' => '276'
        #};
    if ($ma_map{$r->{DATACOLLECTIONEVENTID_OID}}) {
        printf(qq#INSERT INTO "MEDIAASSET_KEYWORDS" ("ID_OID", "INDEXNAME_EID", "IDX") VALUES (%d, '%s', %d);\n#,
            $ma_map{$r->{DATACOLLECTIONEVENTID_OID}},
            $r->{INDEXNAME_EID}, $r->{IDX}
        );
    } else {
        printf("-- no MediaAsset for %s\n", $r->{DATACOLLECTIONEVENTID_OID});
    }
}
