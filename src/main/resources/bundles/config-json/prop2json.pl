#!/usr/bin/perl
use JSON;
use Data::Dumper;

opendir(D, '..');
my @files = readdir(D);
foreach $pfile (@files) {
    next unless ($pfile =~ /\.properties$/);
    my $base_name = $`;
next unless ($base_name eq 'socialAuth');
    print "$base_name\n";

    if (!open(F, "../$pfile")) {
        warn "could not read $pfile: $!";
        next;
    }

    my $j = {};
    while (<F>) {
        next if (/^\s*#/);
        next if (/^\s*$/);
        chop;
        next unless (/\s*=\s*/);
        my $key = $`;
        my $value = $';
        next unless $key;
        my @key_path = split(/\./, $key);
my $val = 999;
        &set($j, \@key_path, $val);
    }

    open(J, ">$base_name.json");
    print J to_json($j, {pretty => 1, utf8 => 1});
    close(J);
}


sub set {
    my ($j, $path, $val) = @_;
    next unless @$path;
    if (scalar(@$path) == 1) {
        $j->{$path->[0]} = $val;
#print "(A)" . Dumper($j);
        return;
    }
    my $k = shift @$path;
    $j->{$k} = {} unless $j->{$k};
    &set($j->{$k}, $path, $val);
#print "(B)" . Dumper($j);
}
