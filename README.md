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

#### scan

```shell
oam media <media_id> scan <path>
```

Assuming tape is mounted at `path`, then this operation will scan all files under
the `path`, calculating the SHA3-256 of them, and storing them into the database.

Now you know what files are on this media.
The software will also record the size, hash and scan time.

You may use `-b` option to use different buffer size to fit your LTO tape drive's speed.
Default is `-b 128`, aka 128MB.

#### verify

```shell
oam media <media_id> verify <path>
```

Assuming tape is mounted at `path`, then this operation will calculate the hash of
all files under `path`.
Then it will compare the calculated hash with the database to see if anything is wrong.

This will not update the database.
If something is wrong, it will let you know.
Once you fix the file or accept your fate that losing some files, you may run scan again
to update the database with your operation (either remove the damaged file, or update the hash).

You may use `-b` option to use different buffer size to fit your LTO tape drive's speed.
Default is `-b 128`, aka 128MB.

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
