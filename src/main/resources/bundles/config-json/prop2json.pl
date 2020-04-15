#!/usr/bin/perl
####
####  note!!!  this is meant to be run ONCE ONLY in order to convert the initial .properties files
####    into .json DEFINITION files ... which can then be edited manually.  for info on the contents of the
####    .json, see README.md
####
####  in short:  do not run this.  :)
####

use utf8;
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
        my $key = &uni($`);
        my $value = &uni($');
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
        };
        my $defn = {
            #id => $key_id,
            required => JSON::false,
            type => $type,
            formSchema => $formSchema,
        };
        $defn->{defaultValue} = $value if (defined $value);

        my $meta = { '__meta' => $defn };
        &set($j, \@key_path, $meta, $key_prefix);
    }

    open(J, ">$base_name.json");
    print J to_json($j, {pretty => 1, utf8 => 1});
    close(J);
#print "\n==========\n" . to_json($j, {pretty => 1, utf8 => 1});
}

#now we do a test one just so we have it to play with
open(J, ">test.json");
print J to_json({
    foo => {
        bar => {
            '__meta' => {
                formSchema => {
                },
                required => JSON::true,
                type => 'string',
                defaultValue => 'BAR',
            },
        },
        initiationDate => {
            '__meta' => {
                formSchema => {
                },
                required => JSON::false,
                type => 'date',
                defaultValue => { macro => 'now' },
            },
        },
    }
}, {pretty => 1, utf8 => 1});
close(J);
print "test\n";


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


sub uni {
    my $in = shift;
    while ($in =~ /\\u([0-9a-f]{4})/i) {
        $in = $` . chr(hex($1)) . $';
    }
    return $in;
}


