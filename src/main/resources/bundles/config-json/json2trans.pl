#!/usr/bin/perl
####
#### this reads .json files and generates a list of ids, primarily for translation
####

use JSON;
use Data::Dumper;
my $KEY_DELIM = '_';
my @FIELDS = qw( label description alt help );

print "{\n";

my $prefix = &make_id('configuration', "");
&expand($prefix);

opendir(D, '.');
my @files = readdir(D);
foreach $jfile (@files) {
    next unless ($jfile =~ /\.json$/);
    my $base_name = $`;
#next unless ($base_name =~ /^(socialAuth|googleKeys)$/);
    warn "$base_name\n";

    if (!open(F, "$jfile")) {
        warn "could not read $jfile: $!";
        next;
    }
    my $j;
    eval { $j = from_json(join('', <F>)); };
    close(F);
    if (!$j) {
        warn "could not parse json in $jfile";
        next;
    }

    my $prefix = &make_id('configuration', $base_name);
    &expand($prefix);

    foreach my $key (keys %$j) {
        my $id = &make_id($prefix, $key);
        &expand($id);
        &traverse($j->{$key}, $id);
    }
    print "\n";
}
print "}\n";


sub traverse {
    my ($j, $prefix) = @_;
    return if (ref($j) ne ref({}));
    foreach my $key (keys %$j) {
        next if ($key =~ /^_/);
        my $id = &make_id($prefix, $key);
        &expand($id);
        &traverse($j->{$key}, $id);
    }
}

sub expand {
    my $prefix = shift;
    foreach my $f (@FIELDS) {
        my $id = &make_id($prefix, $f);
        printf(qq'\t"%s": null,\n', uc($id));
    }
}


sub make_id {
    my $id = join($KEY_DELIM, @_);
    $id =~ s/[\s\-]/_/g;
    return $id;
}
