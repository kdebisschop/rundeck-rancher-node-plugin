#!/usr/bin/perl

use strict;
use warnings;

my %handles;
my $stack;
my $buffer;
my $channel;
my $newStack;
my $newChannel;

$_ = <>;
if (m/^[0-9:]+ \[[^@]+@[a-z]+_([a-z0-9-]+?)-[0-9] [^]]+\]\[([A-Z]+)\] /) {
	$stack = $1;
	$channel = $2;
	open( $handles{$stack}, '>', $stack );
	s/^[0-9:]+ \[[^@]+@[a-z]+_([a-z0-9-]+?)-[0-9] [^]]+\]\[([A-Z]+)\] //;
	$buffer = $_;
}

while (<>) {
	if (m/^[0-9:]+ \[[^@]+@[a-z]+_([a-z0-9-]+?)-[0-9] [^]]+\]\[([A-Z]+)\] /) {
		$newStack = $1;
		$newChannel = $2;
		if ( !$handles{$newStack} ) {
			open( $handles{$newStack}, '>', $newStack );
		}
		s/^[0-9:]+ \[[^@]+@[a-z]+_([a-z0-9-]+?)-[0-9] [^]]+\]\[([A-Z]+)\] //;
		if ($newStack eq $stack && $newChannel eq $channel) {
			chomp $buffer;
			chomp $buffer;
			$buffer .= $_;
		} else {
			if ($buffer =~ m/\S/ms) {
				$buffer =~ s/^/$channel /mg;
				print { $handles{$stack} } $buffer ;
			}
			$channel = $newChannel;
			$buffer = $_;
		}
		$stack = $newStack;
	} else {
		$buffer .= $_;
	}
}

if ($buffer =~ m/\S/ms) {
	$buffer =~ s/^/$channel /mg;
	print { $handles{$stack} } $buffer;
}
