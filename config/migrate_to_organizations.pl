#!/usr/bin/perl
###  apt-get install libdbd-pg-perl libdbi-perl libjson-perl libuuid-tiny-perl


my ($db_name,$db_user,$db_password, $input_file) = @ARGV;

# usage:
#  (1)  migrate_to_organizations.pl DBNAME DBUSER DBPASSWORD > datfile.json
#  (2)  edit datfile.json
#  (3)  migrate_to_organizations.pl DBNAME DBUSER DBPASSWORD datfile.json > commands.sql
#  (4)  use commands.sql !!

use DBI;
use Data::Dumper;
use JSON;
use UUID::Tiny ':std';
binmode STDOUT, ":encoding(utf8)";

my $db = DBI->connect("dbi:Pg:dbname=$db_name;host=localhost;port=5432", $db_user, $db_password);
&do_input($input_file) if $input_file;  #will exit

my $q = $db->prepare('SELECT * FROM "USERS"');
$q->execute;

my $data;
while (my $u = $q->fetchrow_hashref) {
    next unless ($u->{AFFILIATION} || $u->{USERPROJECT});
    utf8::upgrade($u->{AFFILIATION});
    utf8::upgrade($u->{USERPROJECT});
    &build('af', $u->{AFFILIATION}, $u);
    &build('up', $u->{USERPROJECT}, $u);
}

my @sort = ();
foreach my $k (keys %{$data->{syn}}) {
    push(@sort, "$2:$1") if ($k =~ /^(.+):(.+)$/);
}

print "{\n\"syn\": {\n";
foreach my $k (sort @sort) {
    next unless ($k =~ /^(.+):(\S\S)$/);
    my $real_key = "$2:$1";
    print("\"$real_key\": \n");
    my @unique = do { my %seen; grep { !$seen{$_}++ } @{$data->{syn}->{$real_key}} };
#print Dumper(\@unique) if ($real_key =~ /planet/);
    print to_json(\@unique, {pretty => 1, utf8 => 0}) . ",\n";
}

print "\"0\":{}},\n\n\"data\": \n" . to_json($data->{data}, {pretty=>1, utf8 => 0});
print "}\n";


sub build {
    my ($code, $val, $u) = @_;
    return unless $val;
    my $key = "$code:" . lc($val);
    $data->{data}->{$key}->{name} = $val;
    $data->{data}->{$key}->{url} = $u->{USERURL} if ($u->{USERURL} && !$data->{data}->{$key}->{url});
    $data->{data}->{$key}->{members}->{$u->{UUID}} = $u->{FULLNAME};
    push(@{$data->{syn}->{$key}}, $key);
}


sub do_input {
    my $file = shift;
    open(F,$file) || die $!;
    my $raw = join('', <F>);
    close(F);
    my $data = from_json($raw);
    foreach my $orig_key (sort keys %{$data->{data}}) {
        my $key = &resolve_key($orig_key, $data->{syn});
        if (!$key) {
            print "--     $orig_key skipped!\n";
            delete($data->{data}->{$orig_key});
            next;
        }
        next if ($key eq $orig_key);
        foreach my $uid (keys %{$data->{data}->{$orig_key}->{members}}) {
            $data->{data}->{$key}->{members}->{$uid} = $data->{data}->{$orig_key}->{members}->{$uid};
        }
        $data->{data}->{$key}->{url} = $data->{data}->{$orig_key}->{url} if ($data->{data}->{$orig_key}->{url} && !$data->{data}->{$key}->{url});
        delete($data->{data}->{$orig_key});
    }
#print to_json($data->{data}, { pretty => 1, utf8 => 1 }); exit;

    my $ucount;
    my $now = time * 1000;
    foreach my $key (sort keys %{$data->{data}}) {
        print "\n\n-- $key\n";
        my $id = create_uuid_as_string(UUID_V4);
        my $url = $data->{data}->{$key}->{url};
        if (!$url) {
            $url = 'NULL';
        } elsif ($url !~ /^htt/i) {
            $url = $db->quote("http://$url");
        } else {
            $url = $db->quote($url);
        }
        printf(qq'INSERT INTO "ORGANIZATION" ("ID", "NAME", "URL", "CREATED", "MODIFIED") VALUES (%s, %s, %s, %s, %s);\n',
            $db->quote($id),
            $db->quote($data->{data}->{$key}->{name}),
            $url, $now, $now,
        );

        foreach my $uid (keys %{$data->{data}->{$key}->{members}}) {
            printf(qq'INSERT INTO "ORGANIZATION_MEMBERS" ("USER_ID", "ORGANIZATION_ID", "IDX") VALUES (%s, %s, %d);  -- %s\n',
                $db->quote($uid), $db->quote($id), $ucount->{$id}, $data->{data}->{$key}->{members}->{$uid});
            $ucount->{$id}++;
        }
    }
    exit;
}


sub resolve_key {
    my ($key, $syn) = @_;
    return if ($syn->{$key} && !scalar(@{$syn->{$key}}));  #empty array means skip!
    return $key if (scalar(@{$syn->{$key}}) > 0);  #more than one item means use the key
    #now we have to look for synonyms; annoying!
    foreach my $try (keys %$syn) {
        next unless $try;
        foreach my $i (@{$syn->{$try}}) {
            if ($i eq $key) {
                print "-- using [$try] for [$key]\n";
                return $try;
            }
        }
    }
    print "-- unable to find match for $key in synonyms, failing\n";
    return;
}

