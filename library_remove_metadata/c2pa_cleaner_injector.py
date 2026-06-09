import os
import json
import textwrap
from PIL import Image
import piexif
from piexif import helper
import c2pa

def _read_clean_pem(file_path):
    with open(file_path, "rb") as f:
        data = f.read()
    
    if data.startswith(b'\xef\xbb\xbf'):
        data = data[3:]
    if data.startswith(b'\xff\xfe') or data.startswith(b'\xfe\xff'):
        data = data[2:]
        
    data = data.replace(b'\x00', b'')
    data = data.replace(b'\r\n', b'\n')
    
    return data

def _format_pem_key(raw_key):
    if isinstance(raw_key, bytes):
        text = raw_key.decode("utf-8", errors="ignore")
    else:
        text = raw_key

    if "-----BEGIN" in text:
        return text.strip().encode("utf-8")

    clean_b64 = "".join(text.split())
    wrapped_b64 = textwrap.fill(clean_b64, width=64)
    pem_text = f"-----BEGIN PRIVATE KEY-----\n{wrapped_b64}\n-----END PRIVATE KEY-----"
        
    return pem_text.encode("utf-8")

class C2PACleaner:
    def __init__(self, cert_path, key_path):
        self.cert_path = cert_path
        self.key_path = key_path
        
        self.cert_bytes = _read_clean_pem(self.cert_path)
        dirty_key = _read_clean_pem(self.key_path)
        self.key_bytes = _format_pem_key(dirty_key)

    def process_image(self, input_path, output_path):
        image = Image.open(input_path)
        format_str = image.format.lower() if image.format else "jpeg"
        mime_format = "image/jpeg" if format_str in ["jpeg", "jpg"] else f"image/{format_str}"
        
        temp_path = f"temp_{os.path.basename(input_path)}"
        image.save(temp_path, format=format_str)

        # 1. Only declare the custom actions. Do NOT manually declare 'c2pa.opened'.
        manifest = {
            "claim_generator": "C2PACleanerLib/1.0", 
            "title": "Processed Image",
            "assertions": [
                {
                    "label": "c2pa.actions",
                    "data": {
                        "actions": [
                            {
                                "action": "c2pa.edited",
                                "parameters": {
                                    "description": "Non-C2PA metadata (like EXIF) was removed for privacy."
                                }
                            }
                        ]
                    }
                }
            ]
        }

        builder = c2pa.Builder(json.dumps(manifest))
        
        # -------------------------------------------------------------------
        # CRITICAL STEP 1: Manually inject the original file to preserve history.
        # -------------------------------------------------------------------
        ingredient_data = {
            "title": os.path.basename(input_path),
            "relationship": "parentOf"
        }
        
        try:
            with open(input_path, "rb") as source_stream:
                builder.add_ingredient(json.dumps(ingredient_data), mime_format, source_stream)
        except Exception as e:
            print(f"Info: No previous C2PA data found or failed to add ingredient. ({e})")

        # -------------------------------------------------------------------
        # CRITICAL STEP 2: Tell the Builder our intent is to EDIT.
        # Because we already provided a 'parentOf' ingredient above, the Builder 
        # will see it, hash it, and automatically inject a perfectly-formatted 
        # 'c2pa.opened' action linked to that history!
        # -------------------------------------------------------------------
        builder.set_intent(c2pa.C2paBuilderIntent.EDIT)

        signer_info = c2pa.C2paSignerInfo(
            alg=c2pa.C2paSigningAlg.ES256,
            sign_cert=self.cert_bytes,
            private_key=self.key_bytes,
            ta_url=b"http://timestamp.digicert.com"
        )
        
        signer = c2pa.Signer.from_info(signer_info)

        # Sign the clean pixels.
        with open(temp_path, "rb") as clean_original, open(output_path, "wb") as final_file:
            builder.sign(signer, mime_format, clean_original, final_file)

        if os.path.exists(temp_path):
            os.remove(temp_path)

        return True
class C2PAMetadataInjector:
    def __init__(self, cert_path, key_path):
        """Inicializa las credenciales para firmar el C2PA."""
        self.cert_bytes = _read_clean_pem(cert_path)
        dirty_key = _read_clean_pem(key_path)
        self.key_bytes = _format_pem_key(dirty_key)

    def process_and_inject(self, input_path, output_path):
        """
        Toma una imagen original, le inyecta EXIF falso, declara la acción 
        en un nuevo manifiesto C2PA y conserva el historial previo.
        """
        print(f"[*] Abriendo imagen original: {input_path}")
        image = Image.open(input_path)
        format_str = image.format.lower() if image.format else "jpeg"
        mime_format = "image/jpeg" if format_str in ["jpeg", "jpg"] else f"image/{format_str}"
        
        # 1. Definir los metadatos EXIF falsos
        zeroth_ifd = {
            piexif.ImageIFD.Make: b"FakeCorp",
            piexif.ImageIFD.Model: b"TrackerCam 9000",
            piexif.ImageIFD.Software: b"LocationTracker v1.0"
        }
        exif_ifd = {
            piexif.ExifIFD.UserComment: helper.UserComment.dump("Author: John Doe, Phone: 555-0198"),
            piexif.ExifIFD.DateTimeOriginal: b"2026:06:09 20:40:32"
        }
        gps_ifd = {
            piexif.GPSIFD.GPSLatitudeRef: b"N",
            piexif.GPSIFD.GPSLatitude: ((40, 1), (25, 1), (0, 1)), 
            piexif.GPSIFD.GPSLongitudeRef: b"W",
            piexif.GPSIFD.GPSLongitude: ((3, 1), (42, 1), (0, 1))
        }

        exif_dict = {"0th": zeroth_ifd, "Exif": exif_ifd, "GPS": gps_ifd}
        exif_bytes = piexif.dump(exif_dict)

        # 2. Guardar imagen temporal CON los nuevos metadatos EXIF
        # NOTA: En este punto, Pillow borra el C2PA original del archivo temporal.
        temp_path = f"temp_exif_{os.path.basename(input_path)}"
        image.save(temp_path, format=format_str, exif=exif_bytes)
        print("[*] EXIF metadata injected into temporary file.")

        # 3. Create the C2PA manifest declaring the addition of metadata
        manifest = {
            "claim_generator": "C2PAMetadataInjectorLib/1.0", 
            "title": "Image with Injected EXIF",
            "assertions": [
                {
                    "label": "c2pa.actions",
                    "data": {
                        "actions": [
                            {
                                "action": "c2pa.edited",
                                "parameters": {
                                    "description": "Fake EXIF metadata (GPS, Author, Camera) was added for testing."
                                }
                            }
                        ]
                    }
                }
            ]
        }

        builder = c2pa.Builder(json.dumps(manifest))
        
        # 4. Inyectar la imagen ORIGINAL para conservar su historial C2PA previo
        ingredient_data = {
            "title": os.path.basename(input_path),
            "relationship": "parentOf"
        }
        
        try:
            with open(input_path, "rb") as source_stream:
                builder.add_ingredient(json.dumps(ingredient_data), mime_format, source_stream)
            print("[*] Historial C2PA original recuperado como ingrediente.")
        except Exception as e:
            print(f"[*] Info: La imagen original no tenía C2PA previo o hubo un fallo al leerlo. ({e})")

        # 5. Establecer la intención de Edición para enlazar las acciones criptográficamente
        builder.set_intent(c2pa.C2paBuilderIntent.EDIT)

        # 6. Preparar el firmante
        signer_info = c2pa.C2paSignerInfo(
            alg=c2pa.C2paSigningAlg.ES256,
            sign_cert=self.cert_bytes,
            private_key=self.key_bytes,
            ta_url=b"http://timestamp.digicert.com"
        )
        signer = c2pa.Signer.from_info(signer_info)

        # 7. Firmar combinando el archivo temporal (con EXIF) y el historial recuperado
        with open(temp_path, "rb") as image_with_exif, open(output_path, "wb") as final_file:
            builder.sign(signer, mime_format, image_with_exif, final_file)

        # Limpiar
        if os.path.exists(temp_path):
            os.remove(temp_path)

        print(f"[*] ¡Proceso completado! Archivo final guardado en: {output_path}")
        return True