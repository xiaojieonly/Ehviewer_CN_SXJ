---
name: 2026-06-05-smb-download-location-design
description: Design for adding SMB/SAMBA network storage as a download location without local fallback or synchronization.
metadata:
  type: project
---

# SMB/SAMBA Download Location Design

## Goal

Add license-compatible SMB/SAMBA support so users can choose a network SMB share as the app's download location. The implementation should reuse the existing `UniFile` download abstraction and avoid introducing local/SMB synchronization or automatic fallback behavior.

## Non-goals

- Do not implement automatic fallback from SMB to a local directory.
- Do not synchronize files between SMB and local storage.
- Do not expose SMB storage through Android `DocumentsProvider`.
- Do not add multi-server profile management in the first implementation.
- Do not add Kerberos, SMB encryption policy configuration, or advanced SMB session tuning.

## Scope

The first implementation covers:

1. Add an Apache-2.0-compatible SMB client dependency.
2. Add SMB URI handling to `UniFile`.
3. Add SMB-backed file and directory operations needed by existing download code.
4. Add SMB connection configuration in download settings.
5. Add a folder selection flow for SMB shares.
6. Reuse existing download failure, retry, notification, and logging behavior when SMB is unavailable or writes fail.

## Architecture

### Dependency

Use SMBJ as the SMB client library after confirming the selected version's license is Apache-2.0-compatible with the project. Avoid JCIFS because its license and maintenance status are less suitable for this project.

### Storage abstraction

The project already stores download locations as `UniFile` values in `Settings` and uses `SpiderDen` plus download managers to create directories, create files, stream image data, list files, and delete files.

Add SMB support by extending that abstraction:

- `SmbUriHandler`: recognizes SMB URIs in `UniFile.fromUri(context, uri)`.
- `SmbUniFile`: implements the subset of `UniFile` operations required by downloads.
- `SmbConnection` or equivalent internal wrapper: owns SMBJ connection/session/tree lifecycle for a configured server.

The download location URI should describe the remote path only, for example:

```text
smb://server.example.com/share/path
```

Credentials must not be embedded in the URI.

### Configuration storage

Store SMB connection settings separately from the download location URI:

- host
- port
- share name
- path within the share
- login mode: anonymous/guest or username/password
- username, when applicable
- encrypted password, when applicable

Prefer Android Keystore-backed encryption for stored passwords. If the existing project security utilities do not provide a suitable wrapper, add a small focused wrapper rather than storing passwords in plaintext SharedPreferences.

### Download behavior

When the selected download location is SMB:

- `Settings.getDownloadLocation()` returns an SMB-backed `UniFile`.
- `SpiderDen.prepareDownloadStorage()` creates the gallery directory through `UniFile.ensureDir()`.
- Image writes use `UniFile.openOutputStream()` as they do for local and SAF locations.
- Existing download metadata files such as `.ehviewer` and spider info continue to be written through `UniFile`.

If SMB connection, authentication, permission checks, or file writes fail, the task should follow existing download failure behavior. Do not automatically move the task to a local fallback directory.

## User flow

1. User opens download settings.
2. User chooses the SMB download location option.
3. User enters server, port, login mode, and credentials when required.
4. App connects to the SMB server.
5. App lists available shares or folders using the same general navigation pattern as existing Android file selection.
6. User selects a target folder.
7. App tests read/write access by creating and deleting a temporary marker.
8. App saves the SMB location and credentials.
9. Future downloads use that location until the user changes the download location.

## Error handling

Handle these cases explicitly:

- Invalid host or port: show a configuration error before saving.
- Authentication failure: show an error and keep the previous download location unchanged.
- Share or folder not found: show an error and keep the previous download location unchanged.
- No write permission: fail the pre-save write test and keep the previous download location unchanged.
- Network unavailable during download: let the existing download failure/retry path handle it.
- SMB server disconnects mid-download: let the existing download failure/retry path handle it.

Do not silently switch to local storage.

## Testing

Add or update tests for the lowest-risk units first:

1. URI parsing and `SmbUriHandler` recognition.
2. SMB path normalization and share/path separation.
3. Credential storage behavior, including not writing passwords into the URI.
4. `SmbUniFile` operation mapping for directory creation, file creation, listing, delete, and stream access.
5. Download settings save/load behavior for SMB locations.

Manual testing should cover:

- Anonymous/guest share access.
- Username/password share access.
- Invalid credentials.
- Missing write permission.
- Network disconnect during an active download.
- Existing local and SAF download locations still working.

## Rollout notes

The first version should be conservative: one configured SMB download location, no automatic fallback, and no synchronization. This keeps the feature useful for users with NAS/SAMBA storage while avoiding a much larger distributed-storage design.
