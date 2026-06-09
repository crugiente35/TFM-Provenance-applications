
# Import your working cleaner class
from c2pa_cleaner_injector import C2PACleaner
from c2pa_cleaner_injector import C2PAMetadataInjector

if __name__ == "__main__":
    # Define our paths
    ruta_foto_subida = "woman_2.jpg"
    ruta_foto_segura = "woman_2_cleaned.jpg"
    imagen_with_exif = "woman_2_with_fake_exif.jpg"

    try:
        inyector = C2PAMetadataInjector("certificados/certs.pem", "certificados/privKey.pem")
        exito = inyector.process_and_inject(ruta_foto_subida, imagen_with_exif)
        
        if exito:
            print("-" * 50)
            print("Resultado: La imagen final conserva sus píxeles originales,")
            print("contiene los metadatos EXIF falsos (puedes comprobarlo con exiftool),")
            print("y tiene un bloque C2PA válido que encadena el historial previo.")
            
    except FileNotFoundError:
        print("[!] Error: No se encontraron los certificados o la imagen original.")
    # Step 2: Initialize the cleaner (ensure your certs exist in this directory)
    try:
        limpiador = C2PACleaner("certificados/certs.pem", "certificados/privKey.pem")
    except FileNotFoundError:
        print("[!] Error: Could not find certificates. Make sure 'certificados/certs.pem' exists.")
        exit(1)

    print(f"[*] Processing image: {ruta_foto_subida} -> {ruta_foto_segura}")
    
    # Step 3: Run the cleaning process
    exito = limpiador.process_image(imagen_with_exif, ruta_foto_segura)

    if exito:
        print("-" * 50)
        print("¡Imagen subida! Metadatos borrados, pero C2PA conservado/creado correctamente.")
        print(f"[*] You can now inspect '{ruta_foto_segura}' to verify the EXIF is gone but C2PA exists.")