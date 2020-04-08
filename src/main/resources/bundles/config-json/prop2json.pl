#!/usr/bin/perl
####
####  note!!!  this is meant to be run ONCE ONLY in order to convert the initial .properties files
####    into .json files ... which can then be edited manually.  for info on the contents of the
####    .json, see README.md
####

use JSON;
use Data::Dumper;
my $KEY_DELIM = '_';


#### note:  now we have dropped the generated ids, as they are ... generatable.  :/
#### no sense in storing them here to imply they can be set to something else

opendir(D, '..');
my @files = readdir(D);
foreach $pfile (@files) {
    next unless ($pfile =~ /\.properties$/);
    my $base_name = $`;
#next unless ($base_name =~ /^(socialAuth|googleKeys)$/);
    print "$base_name\n";

    if (!open(F, "../$pfile")) {
        warn "could not read $pfile: $!";
        next;
    }

    my $key_prefix = join($KEY_DELIM, 'configuration', $base_name);
    my $j = {
        #id => &key_id($key_prefix),
    };
    while (<F>) {
        next if (/^\s*#/);
        next if (/^\s*$/);
        chop;
        next unless (/\s*=\s*/);
        my $key = $`;
        my $value = $';
        next unless $key;
        my $type = 'string';  #default

        #some hacky cleanup of default values!
        $value = undef if ($value =~ /(changeme|changme|set_me)/i);

        if ($value =~ /^true$/i) {
            $value = JSON::true;
            $type = 'boolean';
        }
        if ($value =~ /^false$/i) {
            $value = JSON::false;
            $type = 'boolean';
        }
        if ($value =~ /^http/i) {
            $type = 'url';
        }
        if ($value =~ /^\/[\w\/\.]+$/) {
            $type = 'filepath'
        }
        #put these int/double ones last
        if ($value =~ /^[\-\d]+$/) {
            $value += 0;
            $type = 'integer';
        } elsif ($value =~ /^[\-\d\.]+$/) {
            $value += 0;
            $type = 'double';
        }


        my @key_path = split(/\./, $key);
        my $key_prefix = join($KEY_DELIM, 'configuration', $base_name);
        my $key_id = &key_id($key_prefix, \@key_path);

        my $formSchema = {
            required => JSON::false,
            type => $type,
        };
        my $defn = {
            #id => $key_id,
            formSchema => $formSchema,
        };
        $defn->{defaultValue} = $value if (defined $value);

        &set($j, \@key_path, $defn, $key_prefix);
    }

    open(J, ">$base_name.json");
    print J to_json($j, {pretty => 1, utf8 => 1});
    close(J);
#print "\n==========\n" . to_json($j, {pretty => 1, utf8 => 1});
}


sub set {
    my ($j, $path, $val, $key_prefix) = @_;
    next unless @$path;
    if (scalar(@$path) == 1) {
        $j->{$path->[0]} = $val;
#print "(A)" . Dumper($j);
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

