# OfflineArchiveManagement

A software to manage offline archives (mainly on LTO tapes).

## Disclaimer

This software is still under test.
I use it for my personal LTO-6 tapes management.
I know how to recover if things go wrong, but you have to figure out by yourself
if you decide to use this software.

## Prerequisite

1. JRE 17 or higher
2. Mount your tape as LTFS
3. Program `ltfsattr` can be found in `PATH`

The third one is not must have.
But without `ltfsattr`, the program can't find out the physical order of files on a given tape.
Thus result in extra tape winding and rewinding, aka waste of time and device lifespan.

## Functionality

Mainly, there are two types of data records: media, and files on media.

### Media

Media is defined as a container that holds a set of files.
More specifically, one tape cartridge is a media.
This software intends to support more media, maybe offline HDD or something,
but right now LTO tapes that support LTFS are the only media it supports.

For a given media, you need an ID. For LTO tapes, it can be the barcode, or
whatever you assigned to it.
You may operate a given media by using the following command:

```shell
oam media <media_id> <operation>
```

Available operations are:

#### walk

```shell
oam media <media_id> walk <path>...
```

This will walk the given path list and perform some actions.

For option `-a`, it will only calculate the hash of new files and append them into the database.

For option `-u`, it will only calculate the hash of existing files and update their hash.

For option `-v`, it will only calculate the hash of existing files,
and check if they match with the record in the database.
If not match, the file index will be removed from the database.

During the process, the software will calculate the SHA3-256 of them.
You may use `-b` option to use different buffer size to fit your LTO tape drive's speed.

Now you know what files are on this media.
The software will also record the size, hash and scan time.

#### purge

```shell
oam media <media_id> purge
```

Purge the file indexes related to a given media ID.
Like `remove`, but doesn't delete the media itself. 

#### list

```shell
oam media <media_id> list
```

List all known files in the given media.
It will print the hash, size, last seen date and path/name of the file.

Using option `-h` will turn the size from raw byte count to human friendly notation.
Like from `123456789` to `123.4M`.

#### remove

```shell
oam media <media_id> remove
```

Remove the media from the system.
It will delete the file indexes related to this media, then delete the media record itself.

#### info

```shell
oam media <media_id> info
```

This will print the info about a given media, such as media id (you give it), type,
generation and last seen date.

By default, the media type and generations are unknown. You may use:

```shell
oam media <media_id> info set type "tape"
```

to set a media's type to tape, and use:

```shell
oam media <media_id> info set gen "LTO-6"
```

to set the generation to LTO-6.

### File

Files are indexes that represent a file stored in a media.
To keep your data safe, you should make multiple copies across different media.
So that once one copy dies, you have a backup.

To operate with files, you can use command:

```shell
oam file <operation>
```

Operations are:

#### find

```shell
oam file find <keyword>
```

Find the database using a given keyword.
The keyword is search against the path or name of file indexes.

The command will print the matched results, including media id, full path,
size, hash and last seen date.

You may use option `-h` for a human friendly size notation.

#### check

```shell
oam file check
```

Check the database and try to find out the same files.
The software will match files with the same hash and size.
Then it will count how many duplicates each file has.

Using option `-n N` to show only the files with less than N duplicates.

Using option `-m mediaId` to show only the files related to a given media ID.

### Misc

There are also some management commands that do not fit those media and file
style.

#### status

```shell
oam status
```

Print the status of the current setup.

If used with option `-l`, it will list all known medias with their id and other info.

If combined with option `-o N`, then it will only list medias that not been seen for more than N months,
where 1 month is count as 30 days.
This will help you figure out which tape you need to rewind.

#### export and import

```shell
oam export <path/to/file.json>
```

Export the whole database into a json file.

```shell
oam import <path/to/file.json>
```

Import from a json file.
By default, it will skip existing record.
You may use option `-f` to overwrite the existing one.

Use option `-v` to print which records are skipped or overwrote.

## Example

Here is my workflow:

1. Fill a second-hand cartridge with data to be archived, this will show how compression works out on the given dataset.
2. Keep a local copy of those data on test cartridge.
3. Use `oam media local walk -au <path/to/local/data>` to generate a reference hash on the correct data (assuming data on your local drive is correct)
4. Use `oam media <barcode> walk -au <path/to/ltfs>` to calculate the hash of files on tape, then store in the database.
5. Use `oam file check -m local -m <barcode> -n 2` to exam if every file is matched. This command should have no output if everything is correct.
6. Then copy the sample data to other two tapes and check. I keep 3 copies of the same data to ensure safety.

Tips:
+ Using tools like TeraCopy, you can leave a checksum file on the tape. So I can check the file even I lost the database of this software.
+ Using 1 second-hand HPE MP tape with 2 brand-new tapes, 1 for HPE MP, 1 for IBM BaFe. Mixing brands and techs should give my data more chance to survive.
+ Using `oam media <barcod> walk -v <path/to/ltfs>` to check files on tape every 3 or 6 months.
  + This is important when I don't have ideal environment to store those tapes.

Despite tapes are not set and forget, it's still worth working with it since:
1. it doesn't run 7x24, so I don't have to invest a UPS
2. I can check it every 3 or 6 months instead of every month when using UnRaid
3. it doesn't occupy a lot of space
4. tapes are so coooooool!

## Contribute

I never have though someone will contribute to this project. But you're welcome.

You may open an issue for:
+ Unclear documentation for commands or codes.
+ Software malfunction (failed to meet what it suppose to do)
+ data loss
+ something else

I may or may not offer help on your issue, unless I'm paid.

Also, you're welcome to open a pull request to improve the code.
But please understand that for the safety of my own data, I may or may not accept
your pull request even if it's good to have.
Broken changes will not be accepted unless it's too good.
(But don't be afraid, I won't blame you for a try. And open a discussion before making pr
is generally a good practice)
