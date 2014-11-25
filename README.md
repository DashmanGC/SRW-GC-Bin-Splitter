SRW GC Bin Splitter by Dashman
---------------------

The name is a bit incomplete, it should be "Bin Splitter And Merger", but that was a bit too long.

This program is meant to be used with the following files: add00dat.bin, add01dat.bin and bpilot.pak.
It's very likely it will work on other files following similar structures, but stick to those for now.

Those files (as many others) are containers for a number of smaller files. A number in the thousands. 
This program lets you extract all those files from them, as well as merge all the extracted files into a new one that follows the same logic as the original.

Of course, this means that you can edit some of those files and rebuild everything into a new valid container with this program.


How to use this
----------------------

This is a command line program, so open a shell / cmd window.
As always, this is a java applet. You'll need to have Java installed for this to work. 
Put the program in the same folder as the files the program is affecting (I was too lazy to implement subfolder recognition).
The whole process of splitting / merging will most likely take a while (specially with add00dat.bin), please be patient.

** To split:
java -jar bin_splitter.jar -s <filename> [<destination_folder>]

For example, 
java -jar bin_splitter.jar -s bpilot.pak bpilot_folder

If the destination folder is not specified, the files will be extracted to the current folder.
You don't want a couple thousand of files popping up in your current folder. Trust me. Do specify that destination folder.

The split process will create an extra file "file.list" that is needed during the merging process.


** To merge:
java -jar bin_splitter.jar -m <filename> <file_list>

For example,
java -jar bin_splitter.jar -m bpilot_new.pak file.list

Remember that ALL files (the ones that were extracted, modified or not) should be present in the same folder or funny things will happen. Funny as in bad.
